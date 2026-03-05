using System;
using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace CourseSync.Api.Migrations
{
    /// <inheritdoc />
    public partial class AddUserCurrentGroupId : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.AddColumn<Guid>(
                name: "current_group_id",
                table: "users",
                type: "uuid",
                nullable: true);

            migrationBuilder.CreateIndex(
                name: "IX_users_current_group_id",
                table: "users",
                column: "current_group_id");

            migrationBuilder.AddForeignKey(
                name: "FK_users_groups_current_group_id",
                table: "users",
                column: "current_group_id",
                principalTable: "groups",
                principalColumn: "Id",
                onDelete: ReferentialAction.SetNull);
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropForeignKey(
                name: "FK_users_groups_current_group_id",
                table: "users");

            migrationBuilder.DropIndex(
                name: "IX_users_current_group_id",
                table: "users");

            migrationBuilder.DropColumn(
                name: "current_group_id",
                table: "users");
        }
    }
}
